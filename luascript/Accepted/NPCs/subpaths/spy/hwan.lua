-- script for npc in buya
spy_hwan = {
	on_spawn = function(mob)
		mob.side = 2
		mob:sendSide()
	end,

	on_healed = function(mob, healer)
	end,

	on_attacked = function(mob, attacker)
		if attacker.gmLevel == 99 then
			mob_ai_basic.on_attacked(mob, attacker)
		end
	end,

	move = function(mob, target)
	end,

	attack = function(mob, target)
	end,

	after_death = function(mob, block)
	end
}

-- script for interrogation
hwan = {
	interrogate = async(function(player)
		local talking = true
		local counter = 0
		local gfx = {graphic = convertGraphic(63, "monster"), color = 30}
		local mistress = {graphic = convertGraphic(5296, "item"), color = 30}
		while talking do
			player:dialogSeq(
				{
					gfx,
					"** Kau mengikat Hwan erat-erat ke pohon lalu berdiri di baliknya supaya ia tidak bisa melihatmu. **"
				},
				1
			)
			local choices = {
				"Siram kepalanya dengan air agar bangun",
				"Tampar belakang kepalanya",
				"Gelitik dia sampai bangun..."
			}
			local choice = player:menuSeq(
				"Apa yang ingin kau lakukan?",
				choices,
				{}
			)
			if choice == 1 or choice == 2 or choice == 3 then
				player:dialogSeq(
					{gfx, "Apa-apaan INI?! Kau tahu siapa aku?!"},
					1
				)
				local choices = {
					"Sekadar antek kekaisaran yang ingin mati.",
					"Tidak penting siapa kau.",
					"Ayah dari seorang gadis kecil yang manis, yang akan",
					"sangat merindukan ayahnya kalau ia menghilang."
				}
				local choice = player:menuSeq(
					"Bagaimana kau ingin membuatnya jengkel?",
					choices,
					{}
				)
				if choice == 1 then
					player:dialogSeq(
						{
							gfx,
							"Aku tidak akan bilang apa pun! Kau tidak tahu siapa aku?!"
						},
						1
					)
				elseif choice == 2 then
					player:dialogSeq(
						{
							gfx,
							"Aku tidak akan bilang apa pun! Kau tidak tahu siapa aku?!"
						},
						1
					)
				elseif choice == 3 or choice == 4 then
					player:dialogSeq(
						{gfx, "Jangan bawa-bawa Mari! Apa maumu?"},
						1
					)
					local choices = {
						"Aku perlu tahu jalur pengangkutan Permata itu",
						"Aku ingin kau pulang dengan selamat kepada Mari",
						"Aku perlu tahu siapa yang memegang Imperial jewels!"
					}
					local choice = player:menuSeq(
						"Apa yang ingin kau lakukan sekarang setelah perhatian Hwan tertuju padamu?",
						choices,
						{}
					)
					if choice == 1 then
						player:dialogSeq(
							{
								gfx,
								"Haha, kau harus berusaha lebih keras dari itu."
							},
							1
						)
					elseif choice == 2 then
						player:dialogSeq(
							{
								gfx,
								"Sudah kubilang jangan bawa-bawa dia! Mereka akan menjemputku, tahu! Sebentar lagi Pengintai Kekaisaran mendobrak pintu-pintu ini!"
							},
							1
						)
						local choices = {
							"*Tancapkan belatimu ke salah satu tangannya*",
							"Ha! Mereka tidak akan pernah menemukanmu.",
							"Mungkin sebaiknya kita minta Mari datang mencarimu."
						}
						local choice = player:menuSeq(
							"Apa yang kau lakukan sekarang terhadap Hwan yang tidak kooperatif ini?",
							choices,
							{}
						)
						if choice == 1 then
							player:dialogSeq(
								{
									gfx,
									"** Hwan menjerit ** Baik, baik! BAIK! Apa yang ingin kau ketahui, akan kuberitahu..."
								},
								1
							)
							local choices = {
								"Aku perlu tahu di mana Permata itu berada.",
								"Aku perlu tahu ke mana Permata itu dibawa.",
								"Aku ingin kau menggambarkan Permata itu."
							}
							local choice = player:menuSeq(
								"Apa yang kau lakukan sekarang terhadap Hwan yang sudah kooperatif ini?",
								choices,
								{}
							)
							if choice == 1 then
								player:dialogSeq(
									{
										gfx,
										"Benda itu dijaga pengamanan tertinggi di istana. ** Itu bukan jawaban yang kau inginkan **"
									},
									1
								)
							elseif choice == 2 then
								player.quest["spy_trials"] = 13
								player.registry["spy_information"] = player.registry[
									"spy_information"
								] + 1
								player:removeLegendbyName("spy_information")
								player:addLegend(
									"Acquired hidden information " .. player.registry[
										"spy_information"
									] .. " times",
									"spy_information",
									22,
									128
								)
								player:dialogSeq(
									{
										gfx,
										"Mereka lewat Vale, melalui lorong kecil di tenggara",
										"Sekarang kau akan melepaskanku?"
									},
									1
								)
								player:dialogSeq(
									{
										mistress,
										"** Seorang perempuan muncul dari balik pohon dan memberi salam tanpa suara **",
										"Kami memang akan melepaskanmu dan membiarkanmu hidup, supaya kau ingat siapa yang sebenarnya menguasai tanah ini.",
										"Sebut sepatah kata pun soal ini dan kau tidak akan pernah terdengar lagi.",
										"** Nyonya guild memingsankan Hwan lalu memanggil Penjaga Makam **",
										"Pastikan tamu istimewa kita beristirahat dengan nyaman di suatu tempat jauh dari sini.",
										"Cepat urus utusan itu sebelum mereka mencapai Nagnang. Jangan tinggalkan jejak - pakai bahan peledak khusus Guild kami dari toko Pyung di Buya.",
										"Mungkin kau sampai lebih dulu daripada rekan lain yang kami kirim... Aku menunggu sebentar lagi di pohon ini untuk siapa pun di antara kalian yang menuntaskan tugasnya duluan."
									},
									1
								)
								return
							elseif choice == 3 then
								player:dialogSeq(
									{
										gfx,
										"Ini termasuk permata paling berharga yang pernah ditemukan. ** Jelas bukan yang perlu kau ketahui **"
									},
									1
								)
							end
						elseif choice == 2 then
							player:dialogSeq(
								{gfx, "Mereka pasti menemukanku."},
								1
							)
						elseif choice == 3 then
							player:dialogSeq(
								{
									gfx,
									"Kalau kau mau membunuhku, cepat selesaikan."
								},
								1
							)
						end
					elseif choice == 3 then
						player:dialogSeq(
							{
								gfx,
								"Haha, kau harus berusaha lebih keras dari itu."
							},
							1
						)
					end
				end
			end
			if counter == 10 then
				talking = false
				break
			end
		end
	end)
}
