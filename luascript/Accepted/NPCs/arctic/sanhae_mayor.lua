local _waypointId = "sanhae"

SanhaeMayorNpc = {
	click = async(function(player, npc)
		Tools.configureDialog(player, npc)

		local opts = {
			"Sanhae Mayor",
			"Tinggal di Sanhae"
		}

		if (not Waypoint.isEnabled(player, _waypointId)) then
			table.insert(opts, "Waypoint")
		end

		local choice = player:menuString("Halo! Ada yang bisa kubantu hari ini?", opts, {})
		local choice2

		if choice == "Waypoint" then
			Waypoint.add(player, npc, _waypointId)
		elseif choice == "Sanhae Mayor" then
			player:dialogSeq({"Halo. Selamat datang di kota Sanhae."}, 1)

			if player.quest["tutorial_quest"] == 11 then
				player:dialogSeq({"Kau tampak gelisah... memang seharusnya begitu. Ada kekuatan gelap yang bekerja di sini."}, 1)

				choice2 = player:menuString(
					"Jadi, ada yang bisa kubantu hari ini?",
					{"Missing Brother", "Dark Forces?", "Du Mountain?"},
					{}
				)
			end
		elseif choice == "Tinggal di Sanhae" then
			if player.country ~= 2 then
				player:dialogSeq({"Salam. Aku ingin sekali mengizinkanmu tinggal di sini, tetapi hanya orang Buya yang boleh tinggal di kota ini."}, 1)
			else
				if player.registry["home"] == 10 then
					local confirm = player:menuSeq(
						"Kau sudah tinggal di kedai kotaku... apa kau mau pergi secepat ini?",
						{"Ya, aku mau.", "Tidak, aku ingin tinggal."},
						{}
					)

					if confirm == 1 then
						-- leave
						player.registry["home"] = 0
						player:dialogSeq({"Yah, tidak ada yang abadi. Semoga beruntung di kemudian hari."}, 0)

						return
					elseif confirm == 2 then
						player:dialogSeq({"Ah, senang mendengarnya. Semoga kau menyukai layananku di sini."}, 0)
						return
					end
				else
					player:dialogSeq({"Jadi kau ingin tinggal di kedaiku yang sederhana ini? Baiklah, ada kamar untukmu. Tapi ingat, kalau begitu kau akan selalu kembali ke sini, bukan ke kedai-kedai di kota."}, 1)

					local confirm = player:menuSeq(
						"Kau yakin ingin melakukan ini?",
						{"Ya, aku mau.", "Tidak, aku tidak mau."},
						{}
					)

					if confirm == 1 then
						player.registry["home"] = 10
						player:dialogSeq({"Selamat datang di kedaiku, semoga kau betah di sini."}, 0)
						return
					elseif confirm == 2 then
						player:dialogSeq({"Itu pilihanmu. Kamarnya masih banyak kalau nanti kau berubah pikiran."}, 0)
						return
					end
				end
				return
			end
		end

		if choice2 == "Missing Brother" then
			player:dialogSeq(
				{
					"Kasihan sekali orang itu. Ia pergi berburu bersama yang lain dan hilang bersama mereka.",
					"Terkutuklah kekuatan jahat ini; andai ada yang cukup berani mengangkat kutukannya."
				},
				1
			)
		elseif choice2 == "Dark Forces?" then
			player:dialogSeq(
				{
					"Belakangan ini beberapa lelaki kami hilang dari kota.",
					"Mereka pergi berburu ke Gunung Du dan tidak pernah kembali.",
					"Aku khawatir desa kami akan berakhir kalau tidak segera ada tindakan."
				},
				1
			)
		elseif choice2 == "Du Mountain?" then
			player:dialogSeq(
				{
					"Oh, kau baru di tanah ini. Gunung Du ada di sebelah barat kota kami.",
					"Kalau kau kembali lewat jalan yang tadi lalu menuju sisi barat celah utara, kau akan menemukannya.",
					"Tapi kumohon jangan ke sana; sekarang hanya kejahatan yang bersemayam di situ."
				},
				1
			)
		end
	end),

	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		if (speech == "titik jalan" and not Waypoint.isEnabled(player, _waypointId)) then
			Waypoint.add(player, npc, _waypointId)
			return
		end
	end),
}
