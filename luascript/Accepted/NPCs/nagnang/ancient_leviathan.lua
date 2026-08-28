AncientLeviathanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player.level < 12 then
			player:dialogSeq(
				{t, "Kembalilah kalau pencerahanmu sudah bertambah."},
				0
			)
			return
		end

		if player:hasLegend("leviathan_sworn_enemy") then
			local choicea = player:menuSeq(
				"Kau bersikap kasar kepadaku dan kaumku. Karena itu kau harus membayar 1 juta keping supaya aku mau melanjutkan pembicaraan. Kau bersedia?",
				{
					"Ya. Aku menyesali ucapanku dan akan membayarmu.",
					"Tidak. Kau tidak sepadan dengan uang itu."
				},
				{}
			)

			if choicea == 1 then
				if player.money < 1000000 then
					player:dialogSeq(
						{t, "Temui aku lagi kalau emasnya sudah kau punya."},
						0
					)
					return
				end

				player:removeGold(1000000)
				player:removeLegendbyName("leviathan_sworn_enemy")
				player:dialogSeq({t, "Aku memaafkanmu."}, 0)
			elseif choicea == 2 then
				player:dialogSeq(
					{
						t,
						"Kalau begitu PERGI! Dan jangan kembali, kami tidak butuh bantuanmu!"
					},
					0
				)
			end
		end

		if not player:hasLegend("leviathan_sworn_enemy") and not player:hasLegend("leviathan_freed") then
			if player.quest["leviathan"] == 1 then
				player:dialogSeq(
					{
						t,
						"Pergilah selamatkan sanakku dengan jimat yang kuberikan."
					},
					0
				)
				return
			end

			if player.quest["leviathan"] == 2 then
				player:addLegend(
					"Freed Leviathan (" .. curT() .. ")",
					"leviathan_freed",
					7,
					128
				)
				player:dialogSeq(
					{
						t,
						"Dengan sepenuh hati aku berterima kasih karena kau menyelamatkan bahkan satu saja dari kaum muda kami. Kau akan selalu menjadi kawan para Leviathan merdeka.",
						"Sebenarnya ada seorang dari kaummu di gubuk kecil timur laut sini. Ia mungkin bisa membantumu. Ia tidak percaya pada orang asing, tetapi katakan saja Dae-Whan yang mengutusmu."
					},
					1
				)
				return
			end

			player:dialogSeq(
				{
					t,
					"Tidak! Kumohon! Jangan ambil lagi kaum kami!!!",
					"Oh, kau bukan Dia. Maafkan sambutanku tadi. Sudah berbulan-bulan kami kehilangan kaum kami karena seorang lelaki yang tak sanggup kami hancurkan.",
					"Ia terus datang ke sini dari waktu ke waktu, membawa pergi anak-anak kami dan melatih mereka menuruti perintahnya dalam perang.",
					"Beberapa hari lalu ia baru saja ke sini dan membawa sekelompok baru ke lapangan latihannya, tempat ia memaksa mereka bekerja dan diperbudak sampai menjadi monster tanpa pikiran untuknya.",
					"Berhari-hari kuhabiskan hanya untuk membuat satu jimat rapuh ini. Kaumku terikat mantra di dalam kandang. Hanya jimat ini yang bisa membebaskan mereka."
				},
				1
			)

			local choice = player:menuSeq(
				"Bersediakah kau membantu seekor leviathan tua menyelamatkan sanaknya?",
				{"Ya, itu suatu kehormatan.", "Tidak. Kaummu memang pantas menerima nasibnya."},
				{}
			)

			if choice == 1 then
				player.quest["leviathan"] = 1

				-- begins quest
				player:addItem("leviathan_talisman", 1)
				player:dialogSeq(
					{
						t,
						"Terima kasih! Ini satu jimat. Ia hanya bekerja sekali. Karena begitu rapuh dan lama pembuatannya, aku hanya memberimu satu.",
						"Kau harus melangkah tepat di sebelah kaumku yang tertawan. Jimat itu lalu mematahkan mantranya dan hancur jadi debu, dan kaumku akan dipindahkan kembali ke sini."
					},
					1
				)
				player:dialogSeq(
					{
						t,
						"Ia memindah-mindahkan perkemahannya, tetapi kami yakin tempatnya di Timur tanah kelahirannya. Kalau kau pergi ke sana dan membebaskan bahkan satu saja kaumku, aku akan berterima kasih."
					},
					0
				)
			elseif choice == 2 then
				player:addLegend(
					"Musuh bebuyutan para Leviathan (" .. curT() .. ")",
					"leviathan_sworn_enemy",
					7,
					4
				)
				player:dialogSeq(
					{
						t,
						"Kalau begitu PERGI! Dan jangan kembali, kami tidak butuh bantuanmu!"
					},
					0
				)
			end
			return
		end
	end),

	move = function(npc, owner)
		local found
		local moved = true
		local oldside = npc.side
		local checkmove = math.random(0, 10)

		if (npc.retDist <= distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1 and npc.returning == false) then
			npc.returning = true
		elseif (npc.returning == true and npc.retDist > distanceXY(npc, npc.startX, npc.startY) and npc.retDist > 1) then
			npc.returning = false
		end

		if (npc.returning == true) then
			found = toStart(npc, npc.startX, npc.startY)
		else
			if (checkmove >= 4) then
				npc.side = math.random(0, 3)
				npc:sendSide()
				if (npc.side == oldside) then
					moved = npc:move()
				end
			end
		end

		if (found == true) then
			npc.returning = false
		end
	end,
}
